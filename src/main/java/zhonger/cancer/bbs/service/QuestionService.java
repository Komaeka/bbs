package zhonger.cancer.bbs.service;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import zhonger.cancer.bbs.dto.PaginationDTO;
import zhonger.cancer.bbs.dto.QuestionDTO;
import zhonger.cancer.bbs.dto.QuestionQueryDTO;
import zhonger.cancer.bbs.exception.CustomizeErrorCode;
import zhonger.cancer.bbs.exception.CustomizeException;
import zhonger.cancer.bbs.mapper.QuestionExtMapper;
import zhonger.cancer.bbs.mapper.QuestionMapper;
import zhonger.cancer.bbs.mapper.UserMapper;
import zhonger.cancer.bbs.model.Question;
import zhonger.cancer.bbs.model.QuestionExample;
import zhonger.cancer.bbs.model.User;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuestionService {
    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private QuestionExtMapper questionExtMapper;

    public PaginationDTO list(String search,Integer page, Integer size) {
        if (StringUtils.isNotBlank(search)) {
            String[] tags = StringUtils.split(search," ");
            search = Arrays.stream(tags).collect(Collectors.joining("|"));
        }
        PaginationDTO paginationDTO = new PaginationDTO();
        Integer totalPage;
        QuestionQueryDTO questionQueryDTO = new QuestionQueryDTO();
        questionQueryDTO.setSearch(search);
        Integer totalCount =questionExtMapper.countBySearch(questionQueryDTO);
        if(totalCount % size==0){
            totalPage = totalCount/size;
        }else {
            totalPage = totalCount/size+1;
        }
        if (page<1){
            page=1;
        }
        if (page>totalPage){
            page=totalPage;
        }
        paginationDTO.setPagination(totalPage,page);
        Integer offset = page < 1 ? 0 : size * (page - 1);
        QuestionExample questionExample = new QuestionExample();

        questionExample.setOrderByClause("gmt_create desc");
        questionQueryDTO.setSize(size);
        questionQueryDTO.setPage(offset);
        List<Question> questions = questionExtMapper.selectBySearch(questionQueryDTO);
        List<QuestionDTO> questionDTOList = new ArrayList<>();
        for (Question question : questions){
            User user = userMapper.selectByPrimaryKey(question.getCreator());
            QuestionDTO questionDTO = new QuestionDTO();
            BeanUtils.copyProperties(question,questionDTO);
            questionDTO.setUser(user);
            questionDTOList.add(questionDTO);
        }
        paginationDTO.setData(questionDTOList);
        return paginationDTO;
    }

    public PaginationDTO list(Long userId, Integer page, Integer size) {
        PaginationDTO paginationDTO = new PaginationDTO();
        Integer totalPage;
        QuestionExample questionExample =new QuestionExample();
        questionExample.createCriteria().andCreatorEqualTo(userId);
        Integer totalCount =(int) questionMapper.countByExample(questionExample);
        if(totalCount % size==0){
            totalPage = totalCount/size;
        }else {
            totalPage = totalCount/size+1;
        }
        if (page<1){
            page=1;
        }
        if (page>totalPage){
            page=totalPage;
        }
        paginationDTO.setPagination(totalPage,page);
        Integer offset = page < 1 ? 0 : size * (page - 1);
        QuestionExample example = new QuestionExample();
        example.setOrderByClause("gmt_create desc");
        example.createCriteria().andCreatorEqualTo(userId);
        List<Question> questions = questionMapper.selectByExampleWithRowbounds(example,new RowBounds(offset,size));
        List<QuestionDTO> questionDTOList = new ArrayList<>();
        for (Question question : questions){
            User user = userMapper.selectByPrimaryKey(question.getCreator());
            QuestionDTO questionDTO = new QuestionDTO();
            BeanUtils.copyProperties(question,questionDTO);
            questionDTO.setUser(user);
            questionDTOList.add(questionDTO);
        }
        paginationDTO.setData(questionDTOList);
        return paginationDTO;
    }

    public QuestionDTO getById(Long id) {
        Question question = questionMapper.selectByPrimaryKey(id);
        if (question==null){
            throw new CustomizeException(CustomizeErrorCode.QUESTION_NOT_FOUND);
        }
        QuestionDTO questionDTO = new QuestionDTO();
        BeanUtils.copyProperties(question,questionDTO);
        User user = userMapper.selectByPrimaryKey(question.getCreator());
        questionDTO.setUser(user);
        return questionDTO;
    }

    public void createOrUpdate(Question question) {
        if (question.getId()==null){
            //创建
            question.setGmtCreate(System.currentTimeMillis());
            question.setGmtModified(question.getGmtCreate());
            question.setViewCount(0);
            question.setLikeCount(0);
            question.setCommentCount(0);
            questionMapper.insert(question);
        }else {
            //更新
            Question updateQuestion = new Question();
            updateQuestion.setGmtModified(System.currentTimeMillis());
            updateQuestion.setTitle(question.getTitle());
            updateQuestion.setTag(question.getTag());
            updateQuestion.setDescription(question.getDescription());
            QuestionExample example =new QuestionExample();
            example.createCriteria().andIdEqualTo(question.getId());
            int updated = questionMapper.updateByExampleSelective(updateQuestion,example);
            if (updated !=1){
                throw new CustomizeException(CustomizeErrorCode.QUESTION_NOT_FOUND);
            }
        }
    }

    public void inView(Long id) {
        Question question = new Question();
        question.setId(id);
        question.setViewCount(1);
        questionExtMapper.incView(question);
    }

    public List<QuestionDTO> selectRelated(QuestionDTO queryDTO) {
        if (StringUtils.isBlank(queryDTO.getTag())) {
            return new ArrayList<>();
        }
        String[] tags = StringUtils.split(queryDTO.getTag(), ",");
        String regexpTag = Arrays
                .stream(tags)
                .filter(StringUtils::isNotBlank)
                .map(t -> t.replace("+", "").replace("*", "").replace("?", ""))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("|"));
        Question question = new Question();
        question.setId(queryDTO.getId());
        question.setTag(regexpTag);

        List<Question> questions = questionExtMapper.selectRelated(question);
        List<QuestionDTO> questionDTOS = questions.stream().map(q -> {
            QuestionDTO questionDTO = new QuestionDTO();
            BeanUtils.copyProperties(q, questionDTO);
            return questionDTO;
        }).collect(Collectors.toList());
        List<QuestionDTO> questionResult = new ArrayList<>();
        int[] randomList = randomCommon(0,questionDTOS.size()-1,7);
        for (int i =0 ;i<7;i++){
            questionResult.add(questionDTOS.get(randomList[i]));
        }
        return questionResult;
    }

    public Integer deleteQuestion(Long id){
        Integer result = questionMapper.deleteByPrimaryKey(id);
        return result;
    }

    public String selectCharacteristicString(Long id) {
        Question question = questionMapper.selectByPrimaryKey(id);
        String  characteristicString = question.getTag();
        return characteristicString;
    }

    public List<Question> selectAllQuestion() {
        List<Question> questionList = questionExtMapper.selectAllQuestion();
        return questionList;
    }

    public static int[] randomCommon(int min, int max, int n){
        if (n > (max - min + 1) || max < min) {
            return null;
        }
        int[] result = new int[n];
        int count = 0;
        while(count < n) {
            int num = (int) (Math.random() * (max - min)) + min;
            boolean flag = true;
            for (int j = 0; j < n; j++) {
                if(num == result[j]){
                    flag = false;
                    break;
                }
            }
            if(flag){
                result[count] = num;
                count++;
            }
        }
        return result;
    }
}